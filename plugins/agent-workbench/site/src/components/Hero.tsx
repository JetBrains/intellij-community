import { GITHUB_URL, MARKETPLACE_URL, PROVIDERS, TAGLINE } from '../data'

export default function Hero() {
  return (
    <header className="hero">
      <nav className="nav">
        <a className="brand" href="#top">
          <img className="brand-logo" src="/icons/logo.svg" alt="" width={32} height={32} />
          <span>Agent Workbench</span>
        </a>
        <div className="nav-links">
          <a href={GITHUB_URL} target="_blank" rel="noreferrer">
            GitHub
          </a>
          <a className="btn btn-ghost" href={MARKETPLACE_URL} target="_blank" rel="noreferrer">
            Marketplace
          </a>
        </div>
      </nav>

      <div className="hero-inner" id="top">
        <p className="eyebrow">IntelliJ Platform plugin</p>
        <h1>
          Run AI coding agents <span className="grad">inside your IDE</span>
        </h1>
        <p className="lede">{TAGLINE}</p>
        <div className="cta-row">
          <a className="btn btn-primary" href={MARKETPLACE_URL} target="_blank" rel="noreferrer">
            Get from Marketplace
          </a>
          <a className="btn btn-ghost" href={GITHUB_URL} target="_blank" rel="noreferrer">
            View on GitHub
          </a>
        </div>
        <ul className="hero-providers" aria-label="Supported agents">
          {PROVIDERS.map((p) => (
            <li key={p.name}>
              <img src={`/icons/${p.icon}`} alt="" width={18} height={18} />
              <span>{p.name}</span>
            </li>
          ))}
        </ul>
      </div>
    </header>
  )
}
