import type { Feature } from '../data'

export default function FeatureCard({ feature }: { feature: Feature }) {
  return (
    <article className="feature-card">
      <div className="feature-shot">
        <img
          src={`/screenshots/${feature.slug}.svg`}
          alt={`${feature.caption} preview`}
          loading="lazy"
        />
      </div>
      <h3>{feature.title}</h3>
      <p>{feature.blurb}</p>
    </article>
  )
}
